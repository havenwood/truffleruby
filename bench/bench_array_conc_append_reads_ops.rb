require_relative 'common'

N = 1_000 # 10_000_000 / 100 / 100
READS = 100 # 1000
ary = READS.times.to_a
SUM = ary.reduce(:+)

def bench(ary, t)
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

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) {
  ary.clear
  READS.times { |i| ary << i }
}
