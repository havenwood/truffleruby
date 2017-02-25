Thread.abort_on_exception = true
require_relative 'common'

N_THREADS = 200 # 4
OPS = N_THREADS / 2

h = {}

$done = false
$deleted = 0
M = Mutex.new

threads = N_THREADS.times.map { |t|
  # Do inserts early
  # k = t/2
  # h[k] = k if t.even?

  Thread.new {
    Thread.pass until $go
    k = t/2
    if t.odd?
      until deleted = h.delete(k)
      end
      M.synchronize { $deleted += 1 }
    else
      sleep 0.1
      h[k] = k
      $done = true
    end
  }
}

sleep 0.1
$go = true

Thread.pass until $done

until (del = $deleted) == OPS
  p [h.size, del]
  sleep 0.01
end

p h.size
p h

threads.each(&:join)

p h.size
p h

