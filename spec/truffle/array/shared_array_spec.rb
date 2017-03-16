# Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'

describe "Sharing an Array" do
  before :all do
    # Make sure we are sharing
    Thread.new {}.join
  end

  it "shares existing elements" do
    obj1 = Object.new
    obj2 = Object.new
    ary = [obj1, obj2]
    Truffle::Debug.shared?(obj1).should == false
    @shared = ary
    Truffle::Debug.shared?(ary).should == true
    Truffle::Debug.shared?(obj1).should == true
    Truffle::Debug.shared?(obj2).should == true
  end
end

describe "Array#<<" do
  def storage(ary)
    Truffle::Debug.array_storage(ary)
  end

  it "$LOAD_PATH" do
    $: << File.dirname(__FILE__) # Make sure $LOAD_PATH is changed
    storage($LOAD_PATH).should == "FastLayoutLock(Object[])"
  end

  it "$LOADED_FEATURES" do
    $" << __FILE__ # Make sure $LOADED_FEATURES is changed
    storage($LOADED_FEATURES).should == "FastLayoutLock(Object[])"
  end

  it "shares new elements written to a shared Array" do
    @ary = [1,2,3]
    @ary << obj = Object.new
    Truffle::Debug.shared?(obj).should == true

    ary = [1,2,3]
    ary << obj2 = Object.new
    Truffle::Debug.shared?(obj2).should == false
  end
end

describe "Array#[]=" do
  it "shares new elements written to a shared Array" do
    @ary = [1,2,3]
    @ary[2] = obj = Object.new
    Truffle::Debug.shared?(obj).should == true

    @ary[10] = obj2 = Object.new
    Truffle::Debug.shared?(obj2).should == true
  end
end
