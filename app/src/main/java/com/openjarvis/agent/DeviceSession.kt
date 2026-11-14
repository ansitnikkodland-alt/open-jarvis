package com.openjarvis.agent

import kotlinx.coroutines.Job

/** A disabled/reconnected service can never resume work from an older session. */
object DeviceSession {
    private var generation = 0L
    private var enabled = false
    private val jobs = mutableSetOf<Job>()

    @Synchronized fun current(): Long? = if (enabled) generation else null
    @Synchronized fun valid(token: Long): Boolean = enabled && token == generation
    @Synchronized fun start() { stop(); enabled = true }
    @Synchronized fun stop() {
        enabled = false
        generation++
        val cancelled = jobs.toList()
        jobs.clear()
        cancelled.forEach { it.cancel() }
    }
    @Synchronized fun register(token: Long, job: Job): Boolean {
        if (!valid(token)) { job.cancel(); return false }
        jobs.add(job)
        job.invokeOnCompletion { synchronized(this) { jobs.remove(job) } }
        return true
    }
}
