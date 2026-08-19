package by.snegoviki2.key

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import by.snegoviki2.key.ui.theme.BackgroundColor
import by.snegoviki2.key.ui.theme.ButtonChangeColor
import by.snegoviki2.key.ui.theme.ButtonChangeColors
import by.snegoviki2.key.ui.theme.ButtonColor
import by.snegoviki2.key.ui.theme.ButtonColors
import by.snegoviki2.key.ui.theme.ElectronicKeySystemTheme
import by.snegoviki2.key.ui.theme.LoadingIndicatorColor
import by.snegoviki2.key.ui.theme.Typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var uiState by mutableStateOf<KeyUiState>(KeyUiState.Loading)
    private val settingsManager = SettingsManager(this)
    private lateinit var clipboard: ClipboardManager
    private val keyStoreManager = KeyStoreManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElectronicKeySystemTheme {
                Scaffold(modifier = Modifier.fillMaxSize().background(BackgroundColor)) { innerPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(BackgroundColor)
                            .padding(innerPadding)
                    ) {
                        KeyScreen(uiState)
                    }
                }
            }
        }
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun onChangeIdClicked() {
        uiState = KeyUiState.EnterId
    }

    fun onCopyButtonClick() {
        CoroutineScope(Dispatchers.IO).launch {
            val employeeIdText = getString(R.string.toClipboard_copiedIdLabel) + ": " + settingsManager.getEmployeeId()
            val publicKeyText = getString(R.string.toClipboard_copiedPublicKeyLabel) + ": " + keyStoreManager.getPublicKeyInPEM()
            val clip = ClipData.newPlainText(getString(R.string.toClipboard_copiedDataLabel), employeeIdText + "\n" + publicKeyText)
            clipboard.setPrimaryClip(clip)
            runOnUiThread {
                if (clipboard.primaryClip == clip) {
                    Log.i("Clipboard", "Data:\n '{$employeeIdText}\n{$publicKeyText}'\n successfully copied")
                }
            }
        }
    }

    fun onContinueClick(employeeId: String) {
        lifecycleScope.launch {
            settingsManager.setEmployeeId(employeeId)
            keyStoreManager.generateKeyPair()
            uiState = KeyUiState.Ready
        }
    }

    @Composable
    fun KeyScreen(state: KeyUiState) {
        LaunchedEffect(Unit) {
            val employeeId = settingsManager.getEmployeeId()
            if (employeeId.isNotBlank()) {
                uiState = KeyUiState.Ready
            } else {
                uiState = KeyUiState.EnterId
            }
        }

        when (state) {
            KeyUiState.Loading -> LoadingScreen()
            KeyUiState.EnterId -> EnterIdScreen()
            KeyUiState.Ready -> AppWorksInBackgroundScreen()
        }
    }

    @Preview
    @Composable
    fun EnterIdScreen() {
        var employeeId by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            employeeId = settingsManager.getEmployeeId()
        }

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(130.dp)
                )
                Spacer(Modifier.height(80.dp))
                Text(
                    text = stringResource(R.string.enterIdScreen_enterField_label),
                    style = Typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                UnderlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                modifier = Modifier
                    .background(ButtonColor)
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonColors,
                onClick = { onContinueClick(employeeId) }
            ) {
                Text(
                    text = stringResource(R.string.enterIdScreen_buttonStartWork_text),
                    style = Typography.titleLarge,
                )
            }
        }
    }
    @Preview
    @Composable
    fun AppWorksInBackgroundScreen() {
        var employeeId by remember { mutableStateOf("") }
        var publicKey by remember { mutableStateOf("") }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            employeeId = settingsManager.getEmployeeId()
            publicKey = keyStoreManager.getPublicKeyInPEM()
        }

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Spacer(Modifier.height(100.dp))
                Text(
                    text = stringResource(R.string.appWorksInBackgroundScreen_label),
                    style = Typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.appWorksInBackgroundScreen_label2),
                    style = Typography.bodyLarge
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text = stringResource(R.string.appWorksInBackgroundScreen_label_to_id),
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = employeeId,
                    style = Typography.bodyLarge,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.appWorksInBackgroundScreen_label_to_public_key),
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                BasicTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    maxLines = 3,
                    readOnly = true,
                    textStyle = Typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                )
                Spacer(Modifier.height(30.dp))
                Button(
                    onClick = {
                        onCopyButtonClick()
                        Toast.makeText(context, getString(R.string.copyButton_dataCopiedSuccessfully), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonColors(Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent),
                    modifier = Modifier
                        .background(Color.Transparent)
                        .align(Alignment.End)
                ) {
                    Image(
                        painter = painterResource(R.drawable.copy_to_clipboard_image),
                        contentDescription = stringResource(R.string.copyButton_description),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Button(
                modifier = Modifier
                    .background(ButtonChangeColor)
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonChangeColors,
                onClick = { onChangeIdClicked() }
            ) {
                Text(
                    text = stringResource(R.string.appWorksInBackgroundScreen_buttonChangeId_text),
                    style = Typography.titleLarge,
                )
            }
        }
    }

    @Preview(showBackground = true, showSystemUi = true)
    @Composable
    fun LoadingScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = LoadingIndicatorColor,
                strokeWidth = 7.dp
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