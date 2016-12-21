# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../../../ruby/spec_helper'

describe "Array#<<" do
  def storage(ary)
    Truffle::Debug.array_storage(ary)
  end

  before :all do
    # Make sure we are sharing
    Thread.new {}.join
  end

  it "$LOAD_PATH" do
    $: << File.dirname(__FILE__) # Make sure $LOAD_PATH is changed
    storage($LOAD_PATH).should == "Synchronized(Object[])"
  end

  it "$LOADED_FEATURES" do
    $" << __FILE__ # Make sure $LOADED_FEATURES is changed
    storage($LOADED_FEATURES).should == "Synchronized(Object[])"
  end
end
