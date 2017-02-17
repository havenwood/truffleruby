Thread.abort_on_exception = true
require_relative 'common'

N_THREADS = 10

h = {}

threads = N_THREADS.times.map { |t|
  h[t] = t
  Thread.new {
    Thread.pass until $go
    sleep(t/10.0)
    p t
    h.delete(t) if t.even? # t.odd?
  }
}

sleep 0.1
$go = true

until h.size == N_THREADS/2
  # p h.keys
  h.each_pair {|k,v| print k }
  puts
  sleep 0.001
end

p h.keys
p h.size

threads.each(&:join)
