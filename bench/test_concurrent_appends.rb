Thread.abort_on_exception = true

Thread.new {}

ARY = ary = []

p Truffle::Debug.array_storage(ary) if defined?(Truffle)

# ARY << 1
# p Truffle::Debug.array_storage(ary) if defined?(Truffle)

T = 100 # 4*4
N = 1000

T.times.map {
  Thread.new {
    i = 0
    while i < N
      ARY << i
      i += 1
    end
  }
}.each(&:join)

p Truffle::Debug.array_storage(ary) if defined?(Truffle)
puts "Expected: #{T*N}"
p ARY.size
# p ARY
