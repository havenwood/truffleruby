require 'concurrent'

Thread.abort_on_exception = true

a = Concurrent::Array.new
b = Concurrent::Array.new

10.times { |i|
  a << i
  b << i
}

puts "initialized"

$start = false
$wait = false
$lock1 = false
$lock2 = false

t1 = Thread.new do
  Thread.pass until $start
  a.each {
    $lock1 = true
    puts "in 1"
    Thread.pass until $wait
    break b[-1]
  }
end

t2 = Thread.new do
  Thread.pass until $start
  r = []
  b.each {
    $lock2 = true
    puts "in 2"
    Thread.pass until $wait
    break a[-1]
  }
end

$start = true
Thread.pass until $lock1 && $lock2
$wait = true

puts "Deadlock?"

3.times do
  sleep 1

  puts
  puts t1.backtrace
  puts
  puts t2.backtrace
end

p t1.value
p t2.value

puts "Done"
