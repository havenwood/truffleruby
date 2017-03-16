require 'prime'

Thread.abort_on_exception = true
strategy = :LightweightLayoutLock

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

def check_prime(num)
  [Prime.instance.prime?(num), num]
end

POISON_PILL = Object.new()

def run(threads=2, n=2000000)
  limit = n
  batch_size = 1000
  n_threads = threads

  results = Queue.new
  tasks = Queue.new
  ((limit/batch_size).times.collect {|i| (i*batch_size+1).upto((i+1)*batch_size).to_a } + [POISON_PILL] * threads).each { |t| tasks << t }

  threads = []
  n_threads.times { |t|
    threads << Thread.new {
      batch = tasks.pop
      while batch != POISON_PILL
        result = batch.collect { |b| check_prime(b) }
        results << result
        batch = tasks.pop
      end
    }
  }

  threads.each(&:join)

  count = 0
  while !results.empty?
    batch_results = results.pop
    count += batch_results.keep_if { |res| res[0] }.size
  end
  puts count
end

puts "Starting..."
ROUNDS=10
ROUNDS.times {
  t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  run Integer(ARGV[0] || 1)
  t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  dt = (t1-t0)
  puts "run length: #{dt}"
}
