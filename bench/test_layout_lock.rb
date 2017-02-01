Thread.abort_on_exception = true

ary = []
Thread.new{}
Truffle::Array.set_strategy(ary, :LayoutLock)
puts Truffle::Debug.array_storage(ary)

ary << 1
p ary[0]
ary << 2
p ary.last
