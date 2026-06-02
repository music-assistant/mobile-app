package io.music_assistant.client.utils.support

abstract class MockFunction {
    protected abstract fun onReset()

    var wasCalled = false

    protected fun wasCalled() {
        wasCalled = true
    }

    fun reset() {
        wasCalled = false
        onReset()
    }
}

class MockFunction0 : MockFunction(), () -> Unit {
    override fun invoke() {
        wasCalled()
    }

    override fun onReset() {
        // Ignored
    }
}

class MockFunction2<T, U> : MockFunction(), (T, U) -> Unit {
    var arg1: T? = null
        private set

    var arg2: U? = null
        private set

    override fun invoke(p1: T, p2: U) {
        wasCalled()
        arg1 = p1
        arg2 = p2
    }

    override fun onReset() {
        arg1 = null
        arg2 = null
    }
}

class MockFunction3<T, U, V> : MockFunction(), (T, U, V) -> Unit {
    var arg1: T? = null
        private set

    var arg2: U? = null
        private set

    var arg3: V? = null
        private set

    override fun invoke(p1: T, p2: U, p3: V) {
        wasCalled()
        arg1 = p1
        arg2 = p2
        arg3 = p3
    }

    override fun onReset() {
        arg1 = null
        arg2 = null
        arg3 = null
    }
}
