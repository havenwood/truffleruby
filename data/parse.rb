file = ARGV[0]
out = File.basename(file, ".*") + ".csv"

c = File.readlines(file)
c.reject! { |line|
  line.start_with?('$ ') ||
  line.include?('Running export ') ||
  line.include?('Run with strategy')
}
c = c.slice_before(/tool\/jt\.rb ruby /).to_a

File.open(out, "w") do |f|
  c.each { |run|
    bench, name, threads = run.shift.split[-3..-1]
    bench = File.basename(bench)
    threads = Integer(threads)
    run = run.reject { |line| line.start_with?('[') }
    times = run.map { |line| Float(line) }
    times.each.with_index(1) { |time, i|
      f.puts [bench, threads, name, i, time].join(';')
    }
  }
end
