require_relative 'common'

DATA_ELEMENTS = 10_000
RAND_MAX = 1000

def bench(hist, t)
#  random = MyRandom.new(t)
  random = Random.new(t)
  i = 0
  while i < DATA_ELEMENTS
    r = random.next_int(RAND_MAX)
#    r = (random.nextGaussian * RAND_MAX).to_i
    r = -r if r < 0
    hist[r] << i
    i += 1
  end
end

hist = put_if_absent_hash { Truffle::Array.set_strategy([], STRATEGY) }

p measure_ops(hist) {
  hist.clear
}
