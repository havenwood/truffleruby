require_relative 'common'

N_THREADS = Integer(ARGV[0])
PUTS = 1000

def bench(h, t)
  base = t*PUTS
  i = 0
  while i < PUTS
    h[base+i] = t
    i += 1
  end
end

11.times { |i| $un = $go = i }

$run = false

THREADS = N_THREADS.times.map { |t|
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
      Thread.pass until $go
      ops = 0
      while $run
        job.call(t)
        ops += 1
      end
      ret.push ops
    end
  }
  [q, ret]
}

def measure
  n = 15
  results = n.times.map do
    input = {}

    $go = false
    $run = true
    THREADS.each { |q,ret| q.push -> t { bench(input, t) } }
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    $go = true

    sleep 2

    $run = false
    results = THREADS.map { |q,ret| ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    ops = results.reduce(:+)
    dt = (t1-t0) / 1_000_000_000.0
    ops /= dt
    p ops
    ops
  end

  [results.min, results.sort[results.size/2], results.max]
  # results.min
end

p measure
