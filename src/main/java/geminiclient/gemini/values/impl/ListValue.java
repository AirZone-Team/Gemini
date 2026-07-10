package geminiclient.gemini.values.impl;


import geminiclient.gemini.values.ValueParent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ListValue extends ValueParent {

    public int index;

    public List<String> getList() {
        return list;
    }

    public List<String> list;

    public ListValue(String name, String t, String[] list) {
        super(name);
        this.list = Arrays.asList(list);
        this.setMode(t);
    }

    public ListValue(String name, String t, String[] list,Supplier<Boolean> visibility) {
        super(name, visibility);
        this.list = Arrays.asList(list);
        this.setMode(t);
    }

    public String getName() {
        return super.getName();
    }

    public String get() {
        if(index >= list.size() || index < 0) {
            index = 0;
        }

        return list.get(index);
    }

    public void setMode(String mode) {
        this.index = list.indexOf(mode);
        notifyChange();
    }

    public boolean is(String mode) {
        if(index >= list.size() || index < 0) {
            index = 0;
        }

        return list.get(index).equals(mode);
    }

    public void increment() {
        if(index < list.size() - 1) {
            index++;
        } else {
            index = 0;
        }
        notifyChange();
    }

    public void decrement() {
        if(index > 0) {
            index--;
        } else {
            index = list.size() - 1;
        }
        notifyChange();
    }

    public void setList(String[] newList) {
        this.list = new ArrayList<>(Arrays.asList(newList));
        if (index >= this.list.size()) {
            index = 0;
        }
    }

    public void setList(List<String> newList) {
        this.list = new ArrayList<>(newList);
        if (index >= this.list.size()) {
            index = 0;
        }
    }

}
