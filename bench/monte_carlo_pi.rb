require_relative 'common'

T = Integer(ARGV[0] || 4)
N = 400_000_000
ITERATIONS = 10

def estimate_pi(samples)
  inside = 0
  i = 0
  samples_to_f = samples.to_f
  while i < samples
    x = i.to_f / samples_to_f
    y = x
    if (x*x + y*y) <= 1.0
      inside += 1
    end
    i += 1
  end
  inside.to_f / samples_to_f
end

puts "#{T} threads, pi with #{N} samples"

# Warmup
p estimate_pi(1000) * 4.0
p estimate_pi(1001) * 4.0

THREADS = T.times.map {
  q = Queue.new
  ret = Queue.new
  Thread.new {
    ITERATIONS.times do
      job = q.pop
      Thread.pass until $go
      ret.push estimate_pi(N / T)
    end
  }
  [q, ret]
}

ITERATIONS.times do |i|
  $go = false
  THREADS.each { |q,ret| q.push :token }
  $go = true
  t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)

  results = THREADS.map { |q,ret| ret.pop }
  t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
  dt = (t1-t0) / 1_000_000
  pi = results.reduce(:+) * 4.0 / T
  puts "pi ~= #{"%.6f" % pi}; Took #{dt} ms\n"
end

THREADS.each(&:join)
