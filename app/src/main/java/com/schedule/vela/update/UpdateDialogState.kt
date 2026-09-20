package com.schedule.vela.update

sealed interface UpdateDialogState {
    data object None : UpdateDialogState

    data class Available(
        val version: String,
    ) : UpdateDialogState

    data class Downloading(
        val progress: Float,
    ) : UpdateDialogState

    data object AwaitingPermission : UpdateDialogState

    data object NeedPermission : UpdateDialogState
}
