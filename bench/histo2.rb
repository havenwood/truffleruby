N = 100_000
R = 10_000

srand 0

puts "generate data"
data = N.times.map { rand(R) }

puts "analyze histogram"
hist = Hash.new { |h,k| h[k] = [] }
data.each_with_index { |r,i|
  hist[r] << i
}

puts "report"
hist.keys.sort_by { |k| hist[k].size }.each { |i|
  puts "#{i} => #{hist[i].size}" # + ": #{hist[i]}"
}
