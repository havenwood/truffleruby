require_relative 'common'

DATA_ELEMENTS = 10_000
RAND_MAX = 1000

def bench(hist, t)
  random = MyRandom.new(t)
  i = 0
  while i < DATA_ELEMENTS
    r = random.next_int(RAND_MAX)
    hist[r] << i
    i += 1
  end
end

hist = put_if_absent_hash { Truffle::Array.set_strategy([], STRATEGY) }

measure_ops(hist) {
  hist.clear
}
