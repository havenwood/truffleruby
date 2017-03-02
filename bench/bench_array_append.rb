require_relative 'common'

N = 10_000_000 / 5 # / 100 # for interp
ary = []

def bench(ary)
  i = 0
  while i < N
    ary << i
    i += 1
  end
  ary
end

measure_single(ary) {
  ary.clear
  ary << 0
}
