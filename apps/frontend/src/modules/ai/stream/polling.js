export const streamTerminalStates = ['STOPPED', 'FAILED', 'UNKNOWN']

export function createStreamPolling({ getSession, getEvents, onSession, onEvents, onError,
  schedule = setTimeout, unschedule = clearTimeout }) {
  let generation = 0
  let timer = null
  let currentId = null
  let cursor = null
  let seen = new Set()

  function stop() {
    generation++
    currentId = null
    if (timer !== null) unschedule(timer)
    timer = null
  }

  async function query(id, ticket) {
    const current = () => ticket === generation && id === currentId
    try {
      const session = await getSession(id)
      if (!current()) return
      if (!session || session.sessionId !== id) throw new Error('返回的会话编号不匹配')
      onSession(session)
      if (!current()) return
      const page = await getEvents(id, { cursor, limit: session.parameters.maxEventsPerPoll })
      if (!current()) return
      if (!page || page.sessionId !== id || !Array.isArray(page.items)) throw new Error('返回的事件页不匹配')
      const fresh = page.items.filter(event => !seen.has(event.eventId))
      fresh.forEach(event => seen.add(event.eventId))
      if (page.nextCursor != null) cursor = page.nextCursor
      onEvents(fresh, cursor)
      if (!current()) return
      if (streamTerminalStates.includes(session.state)) { stop(); return }
      const delay = Math.min(30000, Math.max(250, session.parameters.pollIntervalMillis))
      timer = schedule(() => { if (current()) { timer = null; query(id, ticket) } }, delay)
    } catch (error) {
      if (!current()) return
      stop()
      onError(error)
    }
  }

  function begin(id, reset) {
    const keptCursor = cursor
    const keptSeen = seen
    stop()
    if (!id) return
    cursor = reset ? null : keptCursor
    seen = reset ? new Set() : keptSeen
    currentId = id
    query(id, generation)
  }

  return { start(id) { begin(id, true) }, resume(id) { begin(id, false) }, stop }
}
