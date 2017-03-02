c = File.readlines(ARGV[0])
c.reject! { |line|
  line.include?('Run with strategy')
}
c = c.slice_before(/^\.\.\/tool\/jt\.rb/).to_a

c.each { |run|
  # p run
  # p run.first
  bench, name, threads = run.shift.split[-3..-1]
  bench = File.basename(bench)
  threads = Integer(threads)
  median = eval(run.last)[1]
  puts [bench, threads, name, median].join(';')
}
