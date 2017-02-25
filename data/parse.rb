c = File.readlines(ARGV[0])
c.reject! { |line|
  line.start_with?('$ ') ||
  line.include?('Running export ')
}
c = c.slice_before(/\[INFO\]/).to_a

c.each { |run|
  bench, name, threads = run.shift.split[-3..-1]
  bench = File.basename(bench)
  threads = Integer(threads)
  median = eval(run.last)[1]
  puts [bench, threads, name, median].join(';')
}
