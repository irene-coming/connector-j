package com.demo.jdbc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MyWriter {

	private BufferedWriter writer = null;

	public MyWriter(String fileName) {
		this(new File(fileName));
	}

	public MyWriter(File file) {
		try {
			writer = new BufferedWriter(new FileWriter(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void write(String str) {
		try {
			writer.write(str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

}
