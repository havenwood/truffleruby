Thread.abort_on_exception = true
require_relative 'common'

N_THREADS = 4

h = {}

$done = false

threads = N_THREADS.times.map { |t|
  Thread.new {
    Thread.pass until $go
    k = t/2
    if t.odd?
      until deleted = h.delete(k)
      end
    else
      sleep 0.1
      h[k] = k
      $done = true
    end
  }
}

sleep 0.1
$go = true

until $done
  p h.size
end

p h.size
p h

threads.each(&:join)

p h.size
p h

