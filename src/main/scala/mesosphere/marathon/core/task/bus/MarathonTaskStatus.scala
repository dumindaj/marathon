package mesosphere.marathon.core.task.bus

import org.apache.mesos.Protos.TaskStatus

sealed trait MarathonTaskStatus {
  def terminal: Boolean = false

  def mesosStatus: Option[TaskStatus]
  def mesosHealth: Option[Boolean] = mesosStatus.flatMap { status =>
    if (status.hasHealthy) Some(status.getHealthy) else None
  }
}

object MarathonTaskStatus {
  def apply(mesosStatus: TaskStatus): MarathonTaskStatus = {
    import org.apache.mesos.Protos.TaskState._
    val constructor: Option[TaskStatus] => MarathonTaskStatus = mesosStatus.getState match {
      case TASK_STAGING  => Staging
      case TASK_STARTING => Starting
      case TASK_RUNNING  => Running
      case TASK_FINISHED => Finished
      case TASK_FAILED   => Failed
      case TASK_KILLED   => Killed
      case TASK_LOST     => Lost
      case TASK_ERROR    => Error
    }
    constructor(Some(mesosStatus))
  }

  /** Marathon has send the launch command to Mesos. */
  case object LaunchRequested extends MarathonTaskStatus {
    override def mesosStatus: Option[TaskStatus] = None
  }

  case class Staging(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus
  case class Starting(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus
  case class Running(mesosStatus: Option[TaskStatus]) extends MarathonTaskStatus

  sealed trait Terminal extends MarathonTaskStatus {
    override def terminal: Boolean = true
  }
  object Terminal {
    def unapply(terminal: Terminal): Option[Terminal] = Some(terminal)
  }

  object WithMesosStatus {
    def unapply(marathonTaskStatus: MarathonTaskStatus): Option[TaskStatus] = marathonTaskStatus.mesosStatus
  }
  /**
    * Marathon has decided to deny the task launch even before communicating it to Mesos,
    * e.g. because of throttling.
    */
  case object LaunchDenied extends Terminal {
    override def mesosStatus: Option[TaskStatus] = None
  }
  case class Finished(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Failed(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Killed(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Lost(mesosStatus: Option[TaskStatus]) extends Terminal
  case class Error(mesosStatus: Option[TaskStatus]) extends Terminal
}