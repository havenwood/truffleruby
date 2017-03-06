require_relative 'common'

DATA_ELEMENTS = 10_000
RAND_MAX = 1000
SIGMA=Math.sqrt(RAND_MAX.to_f)
MIDPOINT=RAND_MAX>>1

def bench(hist, t)
#  random = MyRandom.new(t*1111)
  random = Random.new(t)
  i = 0
  while i < DATA_ELEMENTS
#    r = random.next_int(RAND_MAX)
    r = (random.nextGaussian * SIGMA).to_i + MIDPOINT
    r = -r if r < 0
    hist[r] << i
    i += 1
  end
end

hist = put_if_absent_hash { Truffle::Array.set_strategy([], STRATEGY) }

p measure_ops(hist) {
  j=hist.keys.collect { |k| [k, hist[k].length] }.sort { |x,y| -(x[1] <=> y[1]) }
  p "#{j[0]} --> #{j[-1]}, total: #{j.collect {|c| c[1]}.reduce(:+)}, populated: #{hist.keys.length}/#{RAND_MAX}"
  hist.clear
}
