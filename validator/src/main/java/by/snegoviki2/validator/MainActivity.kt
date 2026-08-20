package by.snegoviki2.validator


import android.annotation.SuppressLint
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import by.snegoviki2.validator.ui.theme.AccessDeniedColor
import by.snegoviki2.validator.ui.theme.AccessGrantedColor
import by.snegoviki2.validator.ui.theme.ButtonColor
import by.snegoviki2.validator.ui.theme.ButtonColors
import by.snegoviki2.validator.ui.theme.CheckingColor
import by.snegoviki2.validator.ui.theme.CheckingScreenProgressBarColor
import by.snegoviki2.validator.ui.theme.ElectronicKeySystemTheme
import by.snegoviki2.validator.ui.theme.ErrorColor
import by.snegoviki2.validator.ui.theme.SetupBackground
import by.snegoviki2.validator.ui.theme.TextColor
import by.snegoviki2.validator.ui.theme.Typography
import by.snegoviki2.validator.ui.theme.WaitingColor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

private const val URL_VERIFY_DATA = "http://10.249.109.147:5000/verify"

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private var uiState by mutableStateOf<ValidatorState>(ValidatorState.Waiting)
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var settingsManager: SettingsManager
    private lateinit var innerPadding: PaddingValues
    private val httpClient: HttpClient = HttpClient(CIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElectronicKeySystemTheme() {
                @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
                Scaffold(Modifier.fillMaxSize()) { innerPadding ->
                    ValidatorScreen(uiState) { uiState = ValidatorState.Waiting }
                    this.innerPadding = innerPadding
                }
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        settingsManager = SettingsManager(this)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (settingsManager.getValidatorIsSetup()) enableReaderMode()
        }
    }

    private fun enableReaderMode() {
        if (nfcAdapter.isEnabled) {
            nfcAdapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
    }

    private fun disableReaderMode() {
        nfcAdapter.disableReaderMode(this)
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }

    private fun readNfcTag(tag: Tag): ByteArray {
        val isoDep = IsoDep.get(tag)
        var jsonBytes: ByteArray
        try {
            isoDep.connect()

            // SELECT AID (F222222222)
            val selectAid = byteArrayOf(
                0x00, 0xA4.toByte(), 0x04, 0x00, 0x05,
                0xF2.toByte(), 0x22, 0x22, 0x22, 0x22,
                0x00
            )
            isoDep.transceive(selectAid)

            // GET DATA
            val getData = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0x00, 0x00)
            val response = isoDep.transceive(getData)

            jsonBytes = response.copyOfRange(0, response.size - 2)

        } finally {
            try {
                isoDep.close()
            } catch (e: IOException) {
            }
        }
        return jsonBytes
    }

    override fun onTagDiscovered(tag: Tag?) {
        uiState = ValidatorState.Checking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var response = ""
                if (tag != null) {
                    val jsonBytes = readNfcTag(tag)
                    if (jsonBytes.isEmpty()) {
                        runOnUiThread {
                            uiState =
                                ValidatorState.Error(getString(R.string.error_card_data_is_empty))
                        }
                    } else {
                        val requestJson = JSONObject(String(jsonBytes, Charsets.UTF_8))
                        val validatorName = settingsManager.getValidatorName()
                        requestJson.put("validator_name", validatorName)

                        response = sendToServer((requestJson.toString()).toByteArray())
                        val responseJson = JSONObject(response)

                        Log.i("MainActivity", "Response from server content:\n${response}")
                        runOnUiThread {
                            uiState = if (responseJson.getBoolean("success")) {
                                ValidatorState.AccessGranted(responseJson.getString("text"))
                            } else {
                                ValidatorState.AccessDenied(responseJson.getString("text"))
                            }
                        }
                    }
                }
            } catch (e: TagLostException) {
                Log.e("OnTagDiscovered", "TagLostException threw:\n$e")
                runOnUiThread {
                    uiState = ValidatorState.Error(getString(R.string.error_lost_card_description))
                }
            } catch (e: IOException) {
                Log.e("OnTagDiscovered", "IOException threw:\n$e")
                runOnUiThread {
                    uiState = ValidatorState.Error(getString(R.string.error_bad_connection_to_server))
                }
            } catch (e: Exception) {
                Log.e("OnTagDiscovered", "Exception threw:\n$e")
                runOnUiThread {
                    uiState = ValidatorState.Error(getString(R.string.error_unknown))
                }
            }
        }
    }

    private suspend fun sendToServer(jsonBytes: ByteArray): String {

        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

        val response: HttpResponse = httpClient.post(URL_VERIFY_DATA) {
            contentType(ContentType.Application.Json)
            setBody(json.toString())
        }
        if (response.status != HttpStatusCode.OK)
            throw IOException(response.status.toString())
        return response.bodyAsText()
    }

    private fun onContinueClick(validatorName: String) {
        lifecycleScope.launch {
            settingsManager.setValidatorName(validatorName)
            uiState = ValidatorState.Waiting
            enableReaderMode()
        }
    }

    @Composable
    fun ValidatorScreen(
        state: ValidatorState,
        onReset: ()-> Unit
    ) {
        LaunchedEffect(Unit) {
            if (!settingsManager.getValidatorIsSetup()) uiState = ValidatorState.Setup
        }
        when (state) {
            ValidatorState.Checking -> CheckingScreen()
            ValidatorState.Setup -> SetupScreen()
            is ValidatorState.AccessGranted -> {
                AccessGrantedScreen(state.message)
                LaunchedEffect(Unit) {
                    delay(3000)
                    onReset()
                }
            }
            is ValidatorState.AccessDenied -> {
                AccessDeniedScreen(state.reason)
                LaunchedEffect(Unit) {
                    delay(6000)
                    onReset()
                }
            }
            is ValidatorState.Error -> {
                ErrorScreen(state.message)
                LaunchedEffect(Unit) {
                    delay(6000)
                    onReset()
                }
            }
            else -> WaitingScreen()
        }
    }

    @Composable
    fun SetupScreen() {
        var validatorName by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            validatorName = settingsManager.getValidatorName();
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(SetupBackground)
                .padding(0.dp,innerPadding.calculateTopPadding(), 0.dp, 0.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Spacer(Modifier.height(210.dp))
                Text(
                    text = stringResource(R.string.text_field_label),
                    style = Typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                UnderlinedTextField(
                    value = validatorName,
                    onValueChange = { validatorName = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                modifier = Modifier
                    .background(ButtonColor)
                    .fillMaxWidth()
                    .height(80.dp+innerPadding.calculateBottomPadding())
                    .padding(0.dp,0.dp,0.dp, innerPadding.calculateBottomPadding()),
                colors = ButtonColors,
                onClick = { onContinueClick(validatorName) }
            ) {
                Text(
                    text = stringResource(R.string.button_continue_text),
                    style = Typography.titleLarge,
                )
            }
        }
    }

    @Preview
    @Composable
    fun WaitingScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WaitingColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(R.drawable.waiting_nfc_image),
                contentDescription = stringResource(R.string.waiting_screen_content_description),
                modifier = Modifier.size(200.dp)
            )
        }
    }

    @Preview
    @Composable
    fun CheckingScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CheckingColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(85.dp))
            CircularProgressIndicator(
                color = CheckingScreenProgressBarColor,
                strokeWidth = 7.dp
            )
            Spacer(Modifier.height(85.dp))
            Text(
                text = stringResource(R.string.checking_screen_title),
                color = TextColor,
                style = Typography.titleLarge
            )
        }
    }

    @Composable
    fun AccessGrantedScreen(message: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AccessGrantedColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(45.dp))
            Image(
                painter = painterResource(R.drawable.access_granted_image),
                contentDescription = null
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = stringResource(R.string.access_granted_screen_title),
                color = TextColor,
                style = Typography.titleLarge
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = TextColor,
                style = Typography.bodyLarge
            )

        }
    }

    @Composable
    fun AccessDeniedScreen(reason: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AccessDeniedColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(45.dp))
            Image(
                painter = painterResource(R.drawable.access_denied_image),
                contentDescription = null
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = stringResource(R.string.access_denied_screen_title),
                color = TextColor,
                style = Typography.titleLarge
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = reason,
                textAlign = TextAlign.Center,
                color = TextColor,
                style = Typography.bodyLarge
            )
        }
    }

    @Composable
    fun ErrorScreen(msg: String) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ErrorColor),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(45.dp))
            Image(
                painter = painterResource(R.drawable.error_image),
                contentDescription = null
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = stringResource(R.string.error_screen_title),
                color = TextColor,
                style = Typography.titleLarge
            )
            Spacer(Modifier.height(45.dp))
            Text(
                text = msg,
                color = TextColor,
                style = Typography.bodyLarge
            )
        }
    }
    @Composable
    fun UnderlinedTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = Typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black)
            )
        }
    }
}