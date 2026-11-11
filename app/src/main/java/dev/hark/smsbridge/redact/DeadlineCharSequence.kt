package dev.hark.smsbridge.redact

class RegexTimeoutException : RuntimeException("regex timeout")

class DeadlineCharSequence(
    private val delegate: CharSequence,
    private val deadlineNanos: Long
) : CharSequence {

    override val length: Int
        get() = delegate.length

    override fun get(index: Int): Char {
        if (System.nanoTime() > deadlineNanos) throw RegexTimeoutException()
        return delegate[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return DeadlineCharSequence(delegate.subSequence(startIndex, endIndex), deadlineNanos)
    }

    override fun toString(): String = delegate.toString()
}
