c = File.readlines(ARGV[0])
c.reject! { |line|
  line.start_with?('$ ') ||
  line.include?('Running export ')
}
c = c.slice_before(/\[INFO\]/).to_a

c.each { |run|
  bench, threads = run.shift.split[-5..-1]
  bench = File.basename(bench)
  threads = Integer(threads)
  run = run.reject { |line| line.start_with?('[') }
  run = run.grep(/\d+\.\d+/)
  times = run.map { |line| Float(line) }
  times.sort!
  median = times[times.size/2]
  min = times.min

  puts [bench, threads, "Ruby", min].join(';')
}
