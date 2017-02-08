require 'concurrent'

Thread.abort_on_exception = true

N_THREADS = 4

# h = Hash.new { |h,k| sleep 0.1; h[k] = [] }
h = Concurrent::Map.new { |h,k| sleep 0.1; h[k] = [] }
h = Concurrent::Map.new { |h,k| sleep 0.1; p [:called]; h.put_if_absent(k, v=[]); v }
h = Concurrent::Map.new { |h,k| sleep 0.1; h.put_if_absent(k, []) || h[k] }

threads = N_THREADS.times.map { |t|
  Thread.new {
    h[0] << t
    h.compute_if_absent(1) { sleep 0.1; [] }
    h[1] << t
  }
}

threads.each(&:join)

p h
p Hash[h.each_pair.to_a]
p h.size
p h[0]
p h[0].size
