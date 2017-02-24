c = File.readlines(ARGV[0])
c.reject! { |line| line.start_with?('$ ') }
c = c.slice_before(/\[INFO\]/).to_a

c.each { |run|
  bench, threads = run.shift.split[-2..-1]
  bench = File.basename(bench)
  run.shift if /^(\d+) threads/ =~ run.first

  times = run.map { |line| Integer(line[/Took (\d+) ms/, 1]) }
  times.sort!
  median = times[times.size/2]

  puts [bench, threads, "C", median].join(';')
}
