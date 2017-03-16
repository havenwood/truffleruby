file = ARGV[0]
out = File.basename(file, ".*") + ".csv"

c = File.readlines(file)
c.reject! { |line|
  line.start_with?('$ ') ||
  line.include?('Running export ')
}
c = c.slice_before(/\[INFO\]/).to_a

File.open(out, "w") do |f|
  c.each { |run|
    bench, name, threads = run.shift.split[-3..-1]
    bench = File.basename(bench)
    threads = Integer(threads)
    run.pop while run.last.start_with?('[GC')
    median = eval(run.last)[1]
    f.puts [bench, threads, name, median].join(';')
  }
end