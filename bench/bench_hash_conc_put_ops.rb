require_relative 'common'

PUTS = 1000

def bench(h, t)
  base = t*PUTS
  i = 0
  while i < PUTS
    h[base+i] = t
    i += 1
  end
end

h = {}
p measure_ops(h) {
  h.clear
}
