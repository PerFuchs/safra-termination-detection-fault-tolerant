import statistics


def difference_in_percent(a, b):
	difference = abs(a - b)
	return round(difference / min(a, b) * 100)

def present_linear_relationship(sorted_configurations, field_name, float_representation=False):
	scale = 0
	next_scale = 0
	for c in sorted_configurations:
		mean = statistics.mean(getattr(c, 'get_' + field_name)())
		if scale == 0:
			scale = mean
			next_scale = mean
		else:
			scale = next_scale
			next_scale = mean
		if float_representation:
			print("FG: %s %s: %f Network: %i Scale: %f" % (c.fault_group, field_name, mean, c.number_of_nodes, mean / scale))
		else:
			print("FG: %s %s: %i Network: %i Scale: %f" % (c.fault_group, field_name, mean, c.number_of_nodes, mean / scale))
	print("\n")
