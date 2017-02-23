c = File.readlines(ARGV[0])
c.reject! { |line| line.start_with?('$ ') }
c = c.slice_before(/\[INFO\]/).to_a

c.each { |run|
  bench, threads = run.shift.split[-2..-1]
  bench = File.basename(bench)
  run.shift if /^\d+$/ =~ run.first

  run.slice_before(/^\w+\(/).each { |strategy|
    name = strategy.shift.chomp
    name = name[/^(\w+)/, 1]
    median = eval(strategy.last)[1]

    puts [bench, threads, name, median].join(';')
  }
}
