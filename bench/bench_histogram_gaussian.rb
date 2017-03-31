require_relative 'common'

# Fixed workload distributed between threads

DATA_ELEMENTS = 4_000_000
RAND_MAX = 5_000

SIGMA = Math.sqrt(RAND_MAX.to_f)
MIDPOINT = RAND_MAX / 2

random = Random.new(0)
DATA = Array.new(DATA_ELEMENTS) {
  (random.nextGaussian * SIGMA).to_i + MIDPOINT
}

def bench(hist, t)
  from, to = thread_partition(t, DATA_ELEMENTS)
  i = from
  while i < to
    hist[DATA[i]] << i
    i += 1
  end
end

hist = put_if_absent_hash { Truffle::Array.set_strategy([], STRATEGY) }

p measure_one_op(hist) {
  hist.clear
  # RAND_MAX.times { |i| hist[i] }
}

