package by.snegoviki2.validator

sealed class ValidatorState {
    object Waiting : ValidatorState()
    object Checking : ValidatorState()
    object Setup : ValidatorState()
    data class AccessGranted(val message: String) : ValidatorState()
    data class AccessDenied(val reason:String) : ValidatorState()
    data class Error(val message:String): ValidatorState()
}