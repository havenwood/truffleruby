require_relative 'common'

# Fixed workload distributed between threads

DATA_ELEMENTS = 4_000_000
RAND_MAX = 10_000 # 65_536

def bench(hist, t)
  from, to = thread_partition(t, DATA_ELEMENTS)
  random = MyRandom.new(t)
  i = from
  while i < to
    r = random.next_int(RAND_MAX)
    hist[r] << i
    i += 1
  end
end

hist = put_if_absent_hash { Truffle::Array.set_strategy([], STRATEGY) }

p measure_one_op(hist) {
  hist.clear
  # RAND_MAX.times { |i| hist[i] }
}

