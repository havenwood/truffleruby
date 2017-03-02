Thread.abort_on_exception = true

unless defined?(Truffle)
  module Truffle
    module Array
      def self.set_strategy(ary, strategy)
        ary
      end
    end
    module Debug
      def self.array_storage(ary)
        ary.class.to_s
      end
    end
  end
end

# Avoid global var invalidation
11.times { |i| $run = $go = i }

def thread_pool_ops
  N_THREADS.times.map { |t|
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
end

def measure_ops(input, &prepare_input)
  threads = thread_pool_ops

  results = ROUNDS.times.map do
    prepare_input.call if prepare_input

    $go = false
    $run = true
    threads.each { |q,ret| q.push -> t { bench(input, t) } }
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    $go = true

    sleep THROUGHPUT_TIME

    $run = false
    results = threads.map { |q,ret| ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)

    ops = results.reduce(:+)
    dt = (t1-t0)
    ops /= dt
    p ops
    ops
  end

  results.shift # discard warmup round
  [results.min, results.sort[ROUNDS/2], results.max, results.reduce(:+).to_f / ROUNDS]
end

def measure_single(input)
  n = 10
  results = SINGLE_ROUNDS.times.map do
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    bench(input)
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    (t1-t0) / 1000.0
  end

  # [results.min, results.sort[results.size/2], results.max]
  p results.min
end

# Parse arguments

if ARGV.size < 1
  raise "Usage: bench STRATEGY [N_THREADS=1]"
end

STRATEGY = ARGV[0].to_sym
N_THREADS = Integer(ARGV[1] || 1)

ROUNDS = 10
THROUGHPUT_TIME = 5
SINGLE_ROUNDS = 100
