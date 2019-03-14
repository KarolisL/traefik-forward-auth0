package dniel.forwardauth.application

interface CommandHandler<in T, out V> {
    fun handle(command: T): V;
}