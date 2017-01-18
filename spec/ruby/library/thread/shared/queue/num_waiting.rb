describe :queue_num_waiting, shared: true do
  # Note: this fails because the threads Array becomes shared,
  # which uses the SafepointManager which interrupts() the
  # first subthread, which after goes back to waiting to pop.
  # But in between, the interrupted thread is not sleeping
  # and is not actually waiting on the Queue condition.
  # Keeping our own count of num_waiting would fix this.
  it "reports the number of threads waiting on the queue" do
    q = @object.call
    threads = []

    5.times do |i|
      q.num_waiting.should == i
      t = Thread.new { q.deq }
      Thread.pass until q.num_waiting == i+1
      threads << t
    end

    threads.each { q.enq Object.new }
    threads.each {|t| t.join }
  end
end
