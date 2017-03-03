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

class MyRandom
  def initialize(seed)
    @seed = seed
  end

  def next
    @seed = ((@seed * 1309) + 13849) & 65535
  end

  def next_int(bound)
    # fair enough for low values of bound
    self.next % bound
  end
end

def thread_partition(t, total_size)
  chunk = total_size / N_THREADS
  if t == N_THREADS-1
    [t*chunk, (t+1)*chunk]
  else
    [t*chunk, total_size]
  end
end

if RUBY_ENGINE == 'truffleruby'
  def put_if_absent_hash(&default)
    Hash.new { |h,k| h[k] = default.call }
  end
else
  def put_if_absent_hash(&default)
    mutex = Mutex.new
    Hash.new { |h,k|
      mutex.synchronize {
        if h.key?(k)
          h[k]
        else
          h[k] = default.call
        end
      }
    }
  end
end

# Avoid global var invalidation
11.times { |i| $run = $go = i }

def stats(results)
  results.shift # discard warmup round
  [results.min, results.sort[results.size/2], results.max, results.reduce(:+).to_f / results.size]
end

def thread_pool_ops(input)
  N_THREADS.times.map { |t|
    q = Queue.new
    ret = Queue.new
    Thread.new {
      while job = q.pop
        ret.push thread_bench(input, t)
      end
    }
    [q, ret]
  }
end

def thread_bench(input, t)
  Thread.pass until $go
  ops = 0
  while $run
    bench(input, t)
    ops += 1
  end
  ops
end

def measure_ops(input, &prepare_input)
  threads = thread_pool_ops(input)

  results = ROUNDS.times.map do
    prepare_input.call if prepare_input

    $go = false
    $run = true
    threads.each { |q,ret| q.push :token }
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

  threads.each { |q,ret| q.push nil }
  threads.each(&:join)

  stats(results)
end

def thread_pool(input)
  N_THREADS.times.map { |t|
    q = Queue.new
    ret = Queue.new
    Thread.new {
      while job = q.pop
        Thread.pass until $go
        ret.push bench(input, t)
      end
    }
    [q, ret]
  }
end

def measure_one_op(input, &prepare_input)
  threads = thread_pool(input)

  results = ROUNDS.times.map do
    prepare_input.call if prepare_input

    $go = false
    $run = true
    threads.each { |q,ret| q.push :token }
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    $go = true

    threads.each { |q,ret| ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    dt = (t1-t0)
    ops = 1.0 / dt
    p ops
    ops
  end

  threads.each { |q,ret| q.push nil }
  threads.each(&:join)

  stats(results)
end

def measure_single(input)
  # Make sure we enable shared objects tracking
  Thread.new {}.join

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
