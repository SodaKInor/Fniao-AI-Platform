export const terminalStates = ['SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED']

export function createJobPolling({ getJob, onUpdate, onError, intervalMs = 2000,
  schedule = setTimeout, unschedule = clearTimeout }) {
  let generation = 0
  let timer = null
  let currentId = null

  function stop() {
    generation++
    currentId = null
    if (timer !== null) unschedule(timer)
    timer = null
  }

  async function query(id, ticket) {
    const current = () => ticket === generation && id === currentId
    try {
      const job = await getJob(id)
      if (!current()) return
      if (!job || job.requestId !== id) throw new Error('返回的任务编号不匹配')
      onUpdate(job)
      if (!current()) return
      if (terminalStates.includes(job.state)) { stop(); return }
      timer = schedule(() => { if (current()) { timer = null; query(id, ticket) } }, intervalMs)
    } catch (error) {
      if (!current()) return
      stop()
      onError(error)
    }
  }

  function start(id) {
    stop()
    if (!id) return
    currentId = id
    query(id, generation)
  }

  return { start, stop }
}
