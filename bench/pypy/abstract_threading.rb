require 'thread'

class ThreadPool
  def initialize(n_threads)
    Thread.abort_on_exception = true

    @queue = Queue.new

    @threads = n_threads.times.map { |t|
      Thread.new {
        while job = @queue.pop
          job.call
        end
      }
    }
  end

  def << job
    @queue << job
    job
  end

  def shutdown
    @threads.each { @queue << nil }
    @threads.each(&:join)
  end
end

class Future
  def initialize(&task)
    @task = task
    @mutex = Mutex.new
    @cond = ConditionVariable.new
  end

  def call
    result = @task.call
    @mutex.synchronize do
      @result = result
      @cond.broadcast
    end
  end

  def get
    @mutex.synchronize do
      until r = @result
        @cond.wait(@mutex)
      end
      r
    end
  end
end
