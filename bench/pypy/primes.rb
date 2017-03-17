require_relative '../compat'
require 'prime'

def check_prime(num)
  [Prime.prime?(num), num]
end

def grouper(n, enum, padvalue = nil)
  groups = enum.each_slice(n).to_a
  groups.last << padvalue until groups.last.size == n
  groups
end

def worker(tasks, results)
  Thread.new {
    while batch = tasks.pop
      results << batch.map { |b| check_prime(b) }
    end
  }
end

def run(n_threads = 2, n = 2_000_000)
  limit = n
  batch_size = 1000

  tasks = Queue.new
  results = []

  groups = grouper(batch_size, (0...limit), 1)
  groups.each { |group| tasks << group }
  n_threads.times { tasks << nil }

  n_threads.times.map {
    worker(tasks, results)
  }.each(&:join)

  count = results.reduce(0) { |sum, batch_results|
    sum + batch_results.count { |is_prime, n| is_prime }
  }
end

puts "Starting..."
10.times {
  threads, n = Integer(ARGV[0]), Integer(ARGV[1])
  t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  result = run(threads, n)
  t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  dt = (t1-t0)
  puts result
  puts dt
}
