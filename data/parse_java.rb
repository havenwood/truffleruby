c = File.readlines(ARGV[0])
c.reject! { |line| line.start_with?('$ ') }
c = c.slice_before(/\[INFO\]/).to_a

c.each { |run|
  bench, threads = run.shift.split[-2..-1]
  bench = File.basename(bench)
  threads = Integer(threads)
  times = run.map { |line| Integer(line) }
  times.sort!
  median = times[times.size/2]

  puts [bench, threads, "Java", median].join(';')
}
