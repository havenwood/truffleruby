R = 10_000
N = 10_000
hist = Hash.new { |h,k| h[k] = [] }
0.upto(N) { |i|
  r = rand(R)
  hist[r] << i
}
# report
hist.keys.sort_by { |k| hist[k].size }.each { |i|
  puts "#{i} => #{hist[i].length}: #{hist[i].inspect}"
}
