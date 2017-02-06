Thread.abort_on_exception = true
require_relative 'common'

N_THREADS = 50

h = Hash.new { |h,k| sleep 0.1; h[k] = [] }
q = Queue.new

threads = N_THREADS.times.map { |t|
  work = Queue.new
  Thread.new {
    while work.pop
      Thread.pass until $go
      h[0] << t
      q << :ok
    end
  }
  work
}

20.times do
  $go = false
  threads.each { |t| t << :work }
  sleep 0.01
  $go = true

  threads.each { q.pop }

  p h
  p h.size
  puts Truffle::Debug.array_storage(h[0])
  p h[0].size
  raise h[0].to_s unless h[0].size == N_THREADS

  h.clear
end
