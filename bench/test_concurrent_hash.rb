Thread.abort_on_exception = true
require_relative 'common'

N_THREADS = 4

h = Hash.new { |h,k| sleep 0.1; h[k] = [] }

threads = N_THREADS.times.map { |t|
  Thread.new {
    h[0] << t
  }
}

threads.each(&:join)

p h
p h.size
puts Truffle::Debug.array_storage(h[0])
p h[0].size
