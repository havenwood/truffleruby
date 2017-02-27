require_relative 'common'

STRATEGY = ARGV[0].to_sym || raise("First argument must be the strategy")
N = 1_000 # 10_000_000 / 100 / 100
READS = 100 # 1000
N_THREADS = Integer(ARGV[1] || 4)
ary = READS.times.to_a
SUM = ary.reduce(:+)

def setup(name)
  eval <<EOR, nil, __FILE__, __LINE__
def bench_#{name}(ary)
  i = 0
  while i < N
    ary << i

    j = 0
    sum = 0
    while j < READS
      sum += ary[j]
      j += 1
    end
    i += 1
  end
  raise sum.to_s unless sum == SUM
  sum
end
# alias bench bench_#{name}
# :bench_#{name}
EOR
end

$run = false

THREADS = N_THREADS.times.map {
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
      ops = 0
      while $run
        job.call
        ops += 1
      end
      ret.push ops
    end
  }
  [q, ret]
}

def measure(input, name)
  meth = setup(name)
  n = 5
  results = eval <<EOR
  n.times.map do
    input.clear
    READS.times { |i| input << i }

    ops = 0
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    $run = true
    THREADS.each { |q,ret| q.push -> { #{meth}(input) } }
    sleep 2
    $run = false
    THREADS.each { |q,ret| ops += ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    dt = (t1-t0) / 1_000_000_000.0
    ops /= dt
    p ops
    ops
  end
EOR

  [results.min, results.sort[results.size/2], results.max]
  # results.min
end

setup(:first)
p bench_first(ary)

Truffle::Array.set_strategy(ary, STRATEGY)
puts Truffle::Debug.array_storage(ary)
p measure(ary, STRATEGY)
