package de.ottonow.scheduledtasks.tracking

import org.aspectj.lang.reflect.MethodSignature
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.scheduling.support.ScheduledMethodRunnable
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import kotlin.collections.HashSet

@Component
open class ScheduledJobTracker @Autowired constructor(
    private val scheduledTaskHolder: ScheduledTaskHolder
) {

    var trackedJobs: MutableSet<TrackedScheduledJob> = HashSet()

    init {
        initJobs()
    }

    private fun initJobs() {
        val scheduledTasks = scheduledTaskHolder.scheduledTasks
        scheduledTasks.forEach {
            val runnable = it.task.runnable as ScheduledMethodRunnable
            val annotation = runnable.method.getAnnotation(
                Scheduled::class.java
            )

            trackedJobs.add(
                TrackedScheduledJob(
                    className = runnable.method.declaringClass.name,
                    methodName = runnable.method.name,
                    settings = Settings(
                        cron = nullIfEmptyString(annotation.cron),
                        fixedRate = nullIfNegativeNumber(annotation.fixedRate),
                        fixedRateString = nullIfEmptyString(annotation.fixedRateString),
                        initialDelay = nullIfNegativeNumber(annotation.initialDelay),
                        initialDelayString = nullIfEmptyString(annotation.initialDelayString),
                        fixedDelay = nullIfNegativeNumber(annotation.fixedDelay),
                        fixedDelayString = nullIfEmptyString(annotation.fixedDelayString)
                    )
                )
            )
        }
    }

    open fun triggerJob(className: String, methodName: String) {
        val job = findJobByClassAndMethod(className, methodName)
        if (job != null && job.latestRun()?.endedAt == null)
            return

        val scheduledTasks = scheduledTaskHolder.scheduledTasks
        val matchingJob = scheduledTasks
            .map { it.task.runnable as ScheduledMethodRunnable }
            .firstOrNull { it.method.declaringClass.name == className && it.method.name == methodName }

        if (matchingJob != null)
            matchingJob.run()
    }

    open fun jobStart(signature: MethodSignature): UUID {
        val uuid = UUID.randomUUID()

        val scheduledJob = get(signature)
        scheduledJob.addRun(
            ScheduledJobRun(
                uuid = uuid,
                startedAt = Instant.now()
            )
        )

        return uuid
    }

    private fun get(signature: MethodSignature): TrackedScheduledJob {
        val className = signature.declaringTypeName
        val methodName = signature.method.name

        return trackedJobs.find { it.className == className && it.methodName == methodName }!!
    }

    fun jobEnd(uuid: UUID, signature: MethodSignature, exception: Throwable?) {
        val className = signature.declaringTypeName
        val methodName = signature.method.name
        val trackedJob = trackedJobs.find { it.className == className && it.methodName == methodName }!!
        trackedJob.endRun(uuid, exception)
    }

    private fun nullIfEmptyString(str: String): String? {
        return if (str.isEmpty()) null else str
    }

    private fun nullIfNegativeNumber(number: Long): Long? {
        return if (number < 0) null else number
    }

    open fun findJobsByClass(className: String): List<TrackedScheduledJob> {
        return trackedJobs.filter { it.className == className }
    }

    open fun findJobByClassAndMethod(className: String, methodName: String): TrackedScheduledJob? {
        return trackedJobs.firstOrNull { it.className == className && it.methodName == methodName }
    }

    open fun findRunByUuid(className: String, methodName: String, uuid: String): ScheduledJobRun? {
        val scheduledJob = findJobByClassAndMethod(className, methodName)
        if (scheduledJob == null)
            return null

        return scheduledJob.runs.firstOrNull { it.uuid.toString() == uuid }
    }
}

