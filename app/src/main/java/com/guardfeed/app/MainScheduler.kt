package com.guardfeed.app

import android.os.Handler
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit

object MainScheduler : Scheduler() {

    private val handler = Handler()
    private val queue = LinkedList<DisposableRunnableWrapper>()

    override fun createWorker(): Worker {
        return MainWorker()
    }

    class MainWorker : Worker() {

        private var isDisposed = false

        override fun isDisposed(): Boolean {
            return isDisposed
        }

        override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable {
            return DisposableRunnableWrapper(run).apply {
                queue.offer(this)
                handler.postDelayed(this, unit.toMillis(delay))
            }
        }

        override fun dispose() {
            isDisposed = true
            queue.forEach {
                handler.removeCallbacks(it)
            }
        }
    }

    class DisposableRunnableWrapper(private val runnable: Runnable) : Disposable, Runnable {

        private var isDisposed = false

        override fun isDisposed(): Boolean {
            return isDisposed
        }

        override fun dispose() {
            isDisposed = true
            queue.remove(this)
        }

        override fun run() {
            if (!isDisposed) {
                runnable.run()
                dispose()
            }
        }
    }

}