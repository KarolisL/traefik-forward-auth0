package dniel.forwardauth.application

class CommandDispatcher() {
    companion object Factory {
        fun handleCommand(
                command: Command
        ) = when (command) {
            is AuthorizeCommandHandler.AuthorizeCommand -> {

            }
            else -> throw IllegalStateException("Unknown command")
        }
    }
}