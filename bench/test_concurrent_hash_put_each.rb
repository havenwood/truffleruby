Thread.abort_on_exception = true
require_relative 'common'

PUTS = 10000
N_THREADS = 10
OPS = PUTS * N_THREADS

h = {}

threads = N_THREADS.times.map { |t|
  Thread.new {
    Thread.pass until $go
    base = t*PUTS
    PUTS.times do |j|
      h[base+j] = t
    end
  }
}

sleep 0.1
$go = true

until h.size == OPS
  p h.size
  h.each_pair { |key, value|
    key != value
  }
end

p h.size
