package by.snegoviki2.key

sealed class KeyUiState {
    object EnterId : KeyUiState()
    object Loading: KeyUiState()
    object Ready : KeyUiState()
}